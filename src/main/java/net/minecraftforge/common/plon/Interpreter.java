package net.minecraftforge.common.plon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;


import static net.minecraftforge.common.plon.AST.*;

/**
 * Created by rainwarrior on 5/18/16.
 */
public abstract class Interpreter
{
    private final Map readFrame = new Map(ImmutableMap.of(makeSymbol("load"), new Load()
    {
        @Override
        protected ISExp read(String location)
        {
            return Interpreter.this.read(location);
        }
    }));

    protected abstract ISExp read(String location);

    public ISExp eval(ISExp exp, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv)
    {
        return eval(exp, new Cons(new Map(topEnv), new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE))));
    }

    public ISExp eval(ISExp exp)
    {
        return eval(exp, new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE)));
    }

    private static final ISExp labelSymbol = makeSymbol("&labeled");
    private static final ISExp functionSymbol = makeSymbol("&function");
    static final ISExp mapSymbol = makeSymbol("&map");

    static int length(ISExp exp)
    {
        if (exp == Nil.INSTANCE || exp == MNil.INSTANCE)
        {
            return 0;
        }
        if (exp instanceof Cons)
        {
            return 1 + length(((Cons) exp).cdr);
        }
        if (exp instanceof Map)
        {
            return ((Map) exp).value.size();
        }
        throw new IllegalArgumentException("Length called neither on a list nor on a map");
    }

    private static IList bind(IList argNames, IList args, IList env)
    {
        if (length(argNames) != length(args))
        {
            throw new IllegalArgumentException("called bind with lists of different length");
        }
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        while (argNames != Nil.INSTANCE)
        {
            builder.put(((Cons) argNames).car, ((Cons) args).car);
            argNames = ((Cons) argNames).cdr;
            args = ((Cons) args).cdr;
        }
        ImmutableMap<ISExp, ISExp> frame = builder.build();
        IMap map;
        if (frame.isEmpty())
        {
            map = MNil.INSTANCE;
        }
        else
        {
            map = new Map(frame);
        }
        return new Cons(map, env);

    }

    private static ISExp makeFunction(IList args, ISExp body, IList env)
    {
        return new Cons(functionSymbol, new Cons(args, new Cons(body, new Cons(env, Nil.INSTANCE))));
    }

    static void addLabel(ImmutableMap.Builder<ISExp, ISExp> frame, ISExp key, ISExp value)
    {
        if(key instanceof Symbol)
        {
            Symbol name = (Symbol) key;
            frame.put(name, new Cons(labelSymbol, new Cons(value, Nil.INSTANCE)));
            return;
        }
        else if(key instanceof Cons && ((Cons) key).car instanceof Symbol)
        {
            Cons cons = (Cons) key;
            Symbol name = (Symbol) cons.car;
            IList args = cons.cdr;
            frame.put(name, new Cons(labelSymbol, new Cons(args, new Cons(value, Nil.INSTANCE))));
            return;
        }
        throw new IllegalArgumentException("can't label: " + key);
    }

    private ISExp unlabel(ISExp value, IList env)
    {
        if(value instanceof Cons && ((Cons) value).car.equals(labelSymbol))
        {
            if(length(value) == 3)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                Cons c3 = (Cons) c2.cdr;
                ISExp args = c2.car;
                ISExp body = c3.car;
                // function label
                if(args instanceof IList)
                {
                    return makeFunction((IList) args, body, env);
                }
            }
            else if (length(value) == 2)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                // reference label
                return eval(c2.car, env);
                /*if(c2.car instanceof Symbol)
                {
                    return lookup(env, (Symbol) c2.car);
                }*/
            }
        }
        return value;
    }

    private ISExp lookup(IList env, Symbol name)
    {
        if (env == Nil.INSTANCE)
        {
            //return Unbound.INSTANCE;
            throw new IllegalStateException("Undefined variable " + name);
        }
        Cons cons = (Cons) env;
        if (cons.car == MNil.INSTANCE)
        {
            return lookup(cons.cdr, name);
        }
        if (cons.car instanceof Map)
        {
            Map map = (Map) cons.car;
            if (map.value.containsKey(name))
            {
                ISExp value = map.value.get(name);
                return unlabel(value, env);
            }
            return lookup(cons.cdr, name);
        }
        throw new IllegalArgumentException("lookup called with a list that has something other than a map:" + cons.car);
    }

    private static ISExp delay(ISExp exp)
    {
        return new Cons(makeSymbol("lambda"), new Cons(Nil.INSTANCE, new Cons(exp, Nil.INSTANCE)));
    }

    private ISExp beval(Cons args, IList env)
    {
        if(args.cdr == Nil.INSTANCE)
        {
            return eval(args.car, env);
        }
        ISExp definitions = args.car;
        if (definitions instanceof Map)
        {
            ImmutableMap.Builder<ISExp, ISExp> frameBuilder = ImmutableMap.builder();
            Map map = (Map) definitions;
            for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
            {
                addLabel(frameBuilder, entry.getKey(), entry.getValue());
            }
            return beval((Cons) args.cdr, new Cons(new Map(frameBuilder.build()), env));
        }
        else
        {
            if(definitions instanceof IList && length(definitions) == 2)
            {
                Cons c1 = (Cons) definitions;
                Cons c2 = (Cons) c1.cdr;
                if (c1.car.equals(makeSymbol("load")) && c2.car instanceof StringAtom)
                {
                    String location = ((StringAtom) c2.car).value;
                    ISExp frameExp = read(location);
                    if (!(frameExp instanceof IMap))
                    {
                        throw new IllegalArgumentException("load called from bind should return map, got: " + frameExp);
                    }
                    if(frameExp == MNil.INSTANCE)
                    {
                        return beval((Cons) args.cdr, env);
                    }
                    return beval((Cons) args.cdr, new Cons(loadFrame((Map) frameExp), env));
                }
            }
        }
        throw new IllegalArgumentException("bind enviroments have to be either maps or load expressions.");
    }

    @SuppressWarnings("StringEquality")
    private ISExp eval(ISExp exp, IList env)
    {
        if (exp instanceof Symbol)
        {
            return lookup(env, (Symbol) exp);
        }
        else if (exp instanceof ArithmSymbol)
        {
            return new ArithmOp((ArithmSymbol) exp);
        }
        else if (exp instanceof IAtom)
        {
            return exp;
        }
        else if (exp instanceof Cons)
        {
            Cons cons = (Cons) exp;
            if (cons.car instanceof Symbol)
            {
                String name = ((Symbol) cons.car).value;
                if (name == "quote")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("quote needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return cdr.car;
                }
                else if (name == "lambda")
                {
                    if (length(cons.cdr) != 2)
                    {
                        throw new IllegalArgumentException("lambda needs 2 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (!(c2.car instanceof IList))
                    {
                        throw new IllegalArgumentException("lambda needs a list as a first argument, got: " + c2.car);
                    }
                    IList args = (IList) c2.car;
                    ISExp body = c3.car;
                    return makeFunction(args, body, env);
                }
                else if (name == "bind")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("bind needs at least 1 argument");
                    }
                    Cons args = (Cons) cons.cdr;
                    return beval(args, env);
                }
                // TODO: macro?
                else if(name == "delay")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay needs 1 argument, got: " + exp);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return eval(delay(cdr.car), env);
                }
                // TODO: macro?
                else if(name == "delay_values")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay_values needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    if (!(cdr.car instanceof IMap))
                    {
                        throw new IllegalArgumentException("delay_values needs a map as a first argument, got: " + cdr.car);
                    }
                    if(cdr.car == MNil.INSTANCE)
                    {
                        return MNil.INSTANCE;
                    }
                    Map map = (Map) cdr.car;
                    ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                    for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                    {
                        builder.put(entry.getKey(), delay(entry.getValue()));
                    }
                    return eval(new Map(builder.build()), env);
                }
            }
            ISExp func = eval(cons.car, env);
            IList args = evlis(cons.cdr, env);
            return apply(func, args);
        }
        else if(exp instanceof Map)
        {
            Map map = (Map) exp;
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
            {
                builder.put(eval(entry.getKey(), env), eval(entry.getValue(), env));
            }
            return new Map(builder.build());
        }
        throw new IllegalStateException("eval of " + exp);
    }

    private ISExp apply(ISExp func, IList args)
    {
        if (func instanceof ICallableExp)
        {
            return ((ICallableExp) func).apply(args);
        }
        if (func instanceof Cons)
        {
            Cons c1 = (Cons) func;
            if (functionSymbol.equals(c1.car) && c1.cdr != Nil.INSTANCE)
            {
                Cons c2 = (Cons) c1.cdr;
                if (c2.car instanceof IList && c2.cdr != Nil.INSTANCE)
                {
                    IList argNames = (IList) c2.car;
                    Cons c3 = (Cons) c2.cdr;
                    ISExp body = c3.car;
                    if (c3.cdr != Nil.INSTANCE)
                    {
                        Cons c4 = (Cons) c3.cdr;
                        ISExp env = c4.car;
                        if (env instanceof IList && c4.cdr == Nil.INSTANCE)
                        {
                            return eval(body, bind(argNames, args, (IList) env));
                        }
                    }
                }
            }
        }
        if (func instanceof Map)
        {
            Map map = (Map) func;
            if(length(args) != 1)
            {
                throw new IllegalArgumentException("Can only apply map to 1 argument");
            }
            Cons c1 = (Cons) args;
            if(map.value.containsKey(c1.car))
            {
                return map.value.get(c1.car);
            }
            throw new IllegalStateException("Map " + map + " doesn't have key " + c1.car);
        }
        throw new IllegalArgumentException("Don't know how to apply: " + func);
    }

    private IList evlis(IList list, IList env)
    {
        if (list == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) list;
        return new Cons(eval(cons.car, env), evlis(cons.cdr, env));
    }

    private static final class FreeVarProvider
    {
        private int nextVar = 0;
        private TypeVar getFreshVar()
        {
            return new TypeVar("T" + nextVar++);
        }
    }

    private void inferBindPair(ImmutableMap.Builder<Symbol, ISType> ctxBuilder, ImmutableMap<Symbol, ISType> context, FreeVarProvider vars, ISExp key, ISExp value)
    {
        if(key instanceof Symbol)
        {
            Symbol name = (Symbol) key;
            ISType type = infer(context, vars, value);
            ctxBuilder.put(name, type);
            return;
        }
        // TODO?
        /*else if(key instanceof Cons && ((Cons) key).car instanceof Symbol)
        {
            Cons cons = (Cons) key;
            Symbol name = (Symbol) cons.car;
            IList args = cons.cdr;
            ctxBuilder.put(name, new Cons(labelSymbol, new Cons(args, new Cons(value, Nil.INSTANCE))));
            return;
        }*/
        throw new IllegalArgumentException("can't type label: " + key);
    }

    private ISType binfer(ImmutableMap<? extends ISExp, ? extends ISType> context, FreeVarProvider vars, Cons args)
    {
        if(args.cdr == Nil.INSTANCE)
        {
            return infer(context, vars, args.car);
        }
        ISExp definitions = args.car;

        IMap defs = null;

        if (definitions instanceof Map)
        {
            defs = (Map) definitions;
        }
        else
        {
            if(definitions instanceof IList && length(definitions) == 2)
            {
                Cons c1 = (Cons) definitions;
                Cons c2 = (Cons) c1.cdr;
                if (c1.car.equals(makeSymbol("load")) && c2.car instanceof StringAtom)
                {
                    String location = ((StringAtom) c2.car).value;
                    ISExp frameExp = read(location);
                    if (!(frameExp instanceof IMap))
                    {
                        throw new IllegalArgumentException("load called from bind should return map, got: " + frameExp);
                    }
                    defs = (IMap) frameExp;
                }
            }
        }
        if(defs == MNil.INSTANCE)
        {
            return binfer(context, vars, (Cons) args.cdr);
        }
        if(defs != null)
        {
            return binfer(frameToContext(context, vars, loadFrame((Map) defs)), vars, (Cons) args.cdr);
        }
        throw new IllegalArgumentException("bind enviroments have to be either direct maps or direct load expressions.");
    }

    private ISType unlabelType(ISExp value, ImmutableMap<? extends ISExp, ? extends ISType> context, FreeVarProvider vars)
    {
        if(value instanceof Cons && ((Cons) value).car.equals(labelSymbol))
        {
            // FIXME
            if(length(value) == 3)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                Cons c3 = (Cons) c2.cdr;
                ISExp args = c2.car;
                ISExp body = c3.car;
                // function label
                if(args instanceof IList)
                {
                    return makeFunctionType((IList) args, body, context, vars);
                }
            }
            else if (length(value) == 2)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                // reference label
                return infer(context, vars, c2.car);
            }
        }
        return value.getType();
    }

    private ImmutableMap<ISExp, ISType> frameToContext(ImmutableMap<? extends ISExp, ? extends ISType> context, FreeVarProvider vars, Map frame)
    {
        ImmutableMap<ISExp, ISType> newContext;
        java.util.Map<ISExp, ISType> ctxBuilder = Maps.newHashMap();
        ctxBuilder.putAll(context);
        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
        {
            // new ones shadow old ones. TODO: unify with the list approach?
            ctxBuilder.put((Symbol) entry.getKey(), vars.getFreshVar());
        }
        newContext = ImmutableMap.copyOf(ctxBuilder);
        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
        {
            unify(newContext.get(entry.getKey()), unlabelType(entry.getValue(), newContext, vars));
            //inferBindPair(ctxBuilder, context, vars, entry.getKey(), entry.getValue());
        }
        return newContext;
    }

    private ImmutableMap<ISExp, ISType> envToContext(FreeVarProvider provider, IList env)
    {
        if (env == Nil.INSTANCE)
        {
            return ImmutableMap.of();
        }
        Cons cons = (Cons) env;
        return frameToContext(envToContext(provider, cons.cdr), provider, (Map) cons.car);
    }

    public ISType infer(ISExp exp, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv)
    {
        FreeVarProvider provider = new FreeVarProvider();
        return infer(envToContext(provider, new Cons(new Map(topEnv), new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE)))), provider, exp);
    }

    public ISType infer(ISExp exp)
    {
        FreeVarProvider provider = new FreeVarProvider();
        return infer(envToContext(provider, new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE))), provider, exp);
    }

    private static ISType reify(ISType type, ImmutableSet<? extends ISType> vars, java.util.Map<TypeVar, TypeVar> newVars, FreeVarProvider varProvider)
    {
        type = find(type);
        if(type instanceof TypeVar)
        {
            if(!vars.contains(type))
            {
                if(!newVars.containsKey(type))
                {
                    newVars.put((TypeVar) type, varProvider.getFreshVar());
                }
                return newVars.get(type);
            }
        }
        if(type instanceof AbsType)
        {
            AbsType absType = (AbsType) type;
            ImmutableList.Builder<ISType> args = ImmutableList.builder();
            for(ISType arg : absType.args)
            {
                args.add(reify(arg, vars, newVars, varProvider));
            }
            return new AbsType(args.build(), reify(absType.ret, vars, newVars, varProvider));
        }
        return type;
    }

    private AbsType makeFunctionType(IList args, ISExp body, ImmutableMap<? extends ISExp, ? extends ISType> context, FreeVarProvider vars)
    {
        if(args == Nil.INSTANCE)
        {
            return new AbsType(ImmutableList.<ISType>of(), infer(context, vars, body));
        }
        java.util.Map<ISExp, ISType> newContext = Maps.newHashMap();
        ImmutableList.Builder<ISType> argTypesBuilder = ImmutableList.builder();
        newContext.putAll(context);
        while(args != Nil.INSTANCE)
        {
            Cons c4 = (Cons) args;
            Symbol arg = (Symbol) c4.car;
            ISType argType = vars.getFreshVar();
            // new names shadow old names
            newContext.put(arg, argType);
            argTypesBuilder.add(argType);
            args = c4.cdr;
        }
        ISType bodyType = infer(ImmutableMap.copyOf(newContext), vars, body);
        return new AbsType(argTypesBuilder.build(), bodyType);
    }

    @SuppressWarnings("StringEquality")
    private ISType infer(ImmutableMap<? extends ISExp, ? extends ISType> context, FreeVarProvider vars, ISExp exp)
    {
        if (exp instanceof Symbol)
        {
            if(!context.containsKey(exp))
            {
                // fixme?
                throw new IllegalStateException("type lookup of unknown symbol " + exp);
            }
            return reify(context.get(exp), ImmutableSet.copyOf(context.values()), Maps.<TypeVar, TypeVar>newHashMap(), vars);
        }
        else if (exp instanceof ArithmSymbol)
        {
            return ArithmOp.makeArithmType((ArithmSymbol) exp);
        }
        else if (exp instanceof Cons)
        {
            Cons cons = (Cons) exp;
            if (cons.car instanceof Symbol)
            {
                String name = ((Symbol) cons.car).value;
                if (name == "quote")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("quote needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return cdr.car.getType();
                }
                else if (name == "lambda")
                {
                    if (length(cons.cdr) != 2)
                    {
                        throw new IllegalArgumentException("lambda needs 2 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (!(c2.car instanceof IList))
                    {
                        throw new IllegalArgumentException("lambda needs a list as a first argument, got: " + c2.car);
                    }
                    IList args = (IList) c2.car;
                    ISExp body = c3.car;
                    return makeFunctionType(args, body, context, vars);
                }
                else if (name == "bind")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("bind needs at least 1 argument");
                    }
                    Cons args = (Cons) cons.cdr;
                    return binfer(context, vars, args);
                }
                // dirty hax
                else if (name == "list")
                {
                    return PrimTypes.List.type;
                }
                else if (name == "map")
                {
                    return PrimTypes.Map.type;
                }
                // even dirtier hax
                else if (name == "car")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("car needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    unify(cdr.getType(), PrimTypes.List.type);
                    return vars.getFreshVar();
                }
                else if (name == "carm")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("car needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    unify(cdr.getType(), PrimTypes.List.type);
                    return vars.getFreshVar();
                }
                else if (name == "delay")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay needs 1 argument, got: " + exp);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return new AbsType(ImmutableList.<ISType>of(), infer(context, vars, cdr.car));
                }
                else if (name == "delay_values")
                {
                    return PrimTypes.Map.type;
                }
            }
            ISType funcType = infer(context, vars, cons.car);
            IList args = cons.cdr;
            ImmutableList.Builder<ISType> argTypesBuilder = ImmutableList.builder();
            while(args != Nil.INSTANCE)
            {
                Cons c2 = (Cons) args;
                argTypesBuilder.add(infer(context, vars, c2.car));
                args = c2.cdr;
            }
            ISType bodyType = vars.getFreshVar();
            unify(funcType, new AbsType(argTypesBuilder.build(), bodyType));
            return bodyType;
        }
        return exp.getType();
    }

    static void unify(ISType first, ISType second)
    {
        ISType firstLink = find(first);
        ISType secondLink = find(second);
        if(firstLink instanceof TypeVar)
        {
            ((TypeVar) firstLink).union(second);
            return;
        }
        else if(secondLink instanceof TypeVar)
        {
            ((TypeVar) secondLink).union(first);
            return;
        }
        else if(firstLink instanceof AbsType && secondLink == PrimTypes.Map.type)
        {
            AbsType type = (AbsType) firstLink;
            if(type.args.size() == 1)
            {
                unify(type.args.get(0), PrimTypes.String.type);
                return;
            }
        }
        else if(firstLink instanceof AbsType && secondLink instanceof AbsType)
        {
            AbsType firstAbs = (AbsType) firstLink;
            AbsType secondAbs = (AbsType) secondLink;
            unify(firstAbs.ret, secondAbs.ret);
            if(firstAbs.args.size() == secondAbs.args.size())
            {
                for(int i = 0; i < firstAbs.args.size(); i++)
                {
                    unify(firstAbs.args.get(i), secondAbs.args.get(i));
                }
                return;
            }
        }
        else if(firstLink.equals(secondLink))
        {
            return;
        }
        throw new IllegalStateException("Type error: can't unify " + firstLink + " and " + secondLink);
    }
}
