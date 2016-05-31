package net.minecraftforge.common.plon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import net.minecraftforge.common.util.DisjointSet;


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
    private static final ISExp macroSymbol = makeSymbol("&macro");
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
                /*else if (name == "macro")
                {
                    // TODO
                    if (length(cons.cdr) != 3)
                    {
                        throw new IllegalArgumentException("macro needs 3 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    Cons c4 = (Cons) c3.cdr;
                    makeMacro(c2.car, c3.car, c4.car);
                }*/
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
                // TODO: macro? primitive? primitive macro?
                else if(name == "fold")
                {
                    Cons c1 = (AST.Cons) evlis(cons.cdr, env);
                    Cons c2 = (AST.Cons) c1.cdr;
                    Cons c3 = (AST.Cons) c2.cdr;

                    ISExp f = c1.car;
                    ISExp ret = c2.car;
                    IList list = (IList) c3.car;
                    while(list != Nil.INSTANCE)
                    {
                        Cons c4 = (Cons) list;
                        ret = apply(f, new Cons(ret, new Cons(c4.car, Nil.INSTANCE)));
                        list = c4.cdr;
                    }
                    return ret;
                }
                ISExp func = lookup(env, (Symbol) cons.car);
                if(isMacro(func))
                {
                    eval(expandMacro(func, cons.cdr), env);
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

    private boolean isMacro(ISExp func)
    {
        if (func instanceof Cons)
        {
            Cons c1 = (Cons) func;
            if (macroSymbol.equals(c1.car) && c1.cdr != Nil.INSTANCE)
            {
                // not checking further, assuming macroSymbol + length are enough
                return true;
            }
        }
        return false;
    }

    private ISExp expandMacro(ISExp func, IList args)
    {
        return apply(func, args);
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
            if ((functionSymbol.equals(c1.car) || macroSymbol.equals(c1.car)) && c1.cdr != Nil.INSTANCE)
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
        private ISExp getFreshVar()
        {
            return makeSymbol("T" + nextVar++);
        }
    }

    private ISExp binfer(Cons args, ImmutableMap<? extends ISExp, ? extends ISExp> context, FreeVarProvider vars, DisjointSet<ISExp> union)
    {
        if(args.cdr == Nil.INSTANCE)
        {
            return infer(args.car, context, vars, union);
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
            return binfer((Cons) args.cdr, context, vars, union);
        }
        if(defs != null)
        {
            return binfer((Cons) args.cdr, frameToContext(loadFrame((Map) defs), context, vars, union), vars, union);
        }
        throw new IllegalArgumentException("bind enviroments have to be either direct maps or direct load expressions.");
    }

    private ISExp unlabelType(ISExp value, ImmutableMap<? extends ISExp, ? extends ISExp> context, FreeVarProvider vars, DisjointSet<ISExp> union)
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
                    return makeFunctionType((IList) args, body, context, vars, union);
                }
            }
            else if (length(value) == 2)
            {
                Cons c2 = (Cons) ((Cons) value).cdr;
                // reference label
                return infer(c2.car, context, vars, union);
            }
        }
        return value.getType();
    }

    private ImmutableMap<ISExp, ISExp> frameToContext(Map frame, ImmutableMap<? extends ISExp, ? extends ISExp> context, FreeVarProvider vars, DisjointSet<ISExp> union)
    {
        ImmutableMap<ISExp, ISExp> newContext;
        java.util.Map<ISExp, ISExp> ctxBuilder = Maps.newHashMap();
        ctxBuilder.putAll(context);
        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
        {
            // new ones shadow old ones. TODO: unify with the list approach?
            ctxBuilder.put((Symbol) entry.getKey(), vars.getFreshVar());
        }
        newContext = ImmutableMap.copyOf(ctxBuilder);
        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
        {
            unify(newContext.get(entry.getKey()), unlabelType(entry.getValue(), newContext, vars, union), union);
            //inferBindPair(ctxBuilder, context, vars, entry.getKey(), entry.getValue());
        }
        return newContext;
    }

    private ImmutableMap<ISExp, ISExp> envToContext(IList env, FreeVarProvider provider, DisjointSet<ISExp> union)
    {
        if (env == Nil.INSTANCE)
        {
            return ImmutableMap.of();
        }
        Cons cons = (Cons) env;
        return frameToContext((Map) cons.car, envToContext(cons.cdr, provider, union), provider, union);
    }

    public ISExp infer(ISExp exp, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv, DisjointSet<ISExp> union)
    {
        FreeVarProvider provider = new FreeVarProvider();
        return infer(exp, envToContext(new Cons(new Map(topEnv), new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE))), provider, union), provider, union);
    }

    public ISExp infer(ISExp exp, DisjointSet<ISExp> union)
    {
        FreeVarProvider provider = new FreeVarProvider();
        return infer(exp, envToContext(new Cons(readFrame, new Cons(new Map(PrimOp.values), Nil.INSTANCE)), provider, union), provider, union);
    }

    private static ISExp reify(ISExp type, ImmutableSet<? extends ISExp> vars, java.util.Map<ISExp, ISExp> newVars, FreeVarProvider varProvider, DisjointSet<ISExp> union)
    {
        type = find(type, union);
        if(type instanceof Symbol)
        {
            if(!vars.contains(type))
            {
                if(!newVars.containsKey(type))
                {
                    newVars.put(type, varProvider.getFreshVar());
                }
                return newVars.get(type);
            }
        }
        if(type instanceof IList)
        {
            return reilist((IList) type, vars, newVars, varProvider, union);
        }
        return type;
    }

    private static IList reilist(IList args, ImmutableSet<? extends ISExp> vars, java.util.Map<ISExp, ISExp> newVars, FreeVarProvider varProvider, DisjointSet<ISExp> union)
    {
        if(args == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) args;
        return new Cons(reify(cons.car, vars, newVars, varProvider, union), reilist(cons.cdr, vars, newVars, varProvider, union));
    }

    private ISExp makeFunctionType(IList args, ISExp body, ImmutableMap<? extends ISExp, ? extends ISExp> context, FreeVarProvider vars, DisjointSet<ISExp> union)
    {
        if(args == Nil.INSTANCE)
        {
            return absType(ImmutableList.<ISExp>of(), infer(body, context, vars, union));
        }
        java.util.Map<ISExp, ISExp> newContext = Maps.newHashMap();
        ImmutableList.Builder<ISExp> argTypesBuilder = ImmutableList.builder();
        newContext.putAll(context);
        while(args != Nil.INSTANCE)
        {
            Cons c4 = (Cons) args;
            Symbol arg = (Symbol) c4.car;
            ISExp argType = vars.getFreshVar();
            // new names shadow old names
            newContext.put(arg, argType);
            argTypesBuilder.add(argType);
            args = c4.cdr;
        }
        ISExp bodyType = infer(body, ImmutableMap.copyOf(newContext), vars, union);
        return absType(argTypesBuilder.build(), bodyType);
    }

    @SuppressWarnings("StringEquality")
    private ISExp infer(ISExp exp, ImmutableMap<? extends ISExp, ? extends ISExp> context, FreeVarProvider vars, DisjointSet<ISExp> union)
    {
        if (exp instanceof Symbol)
        {
            if(!context.containsKey(exp))
            {
                // fixme?
                throw new IllegalStateException("type lookup of unknown symbol " + exp);
            }
            return reify(context.get(exp), ImmutableSet.copyOf(context.values()), Maps.<ISExp, ISExp>newHashMap(), vars, union);
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
                    return makeFunctionType(args, body, context, vars, union);
                }
                else if (name == "bind")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("bind needs at least 1 argument");
                    }
                    Cons args = (Cons) cons.cdr;
                    return binfer(args, context, vars, union);
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
                    unify(infer(cdr.car, context, vars, union), PrimTypes.List.type, union);
                    return vars.getFreshVar();
                }
                else if (name == "carm")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("car needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    unify(infer(cdr, context, vars, union), PrimTypes.List.type, union);
                    return vars.getFreshVar();
                }
                else if (name == "delay")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("delay needs 1 argument, got: " + exp);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return absType(ImmutableList.<ISExp>of(), infer(cdr.car, context, vars, union));
                }
                else if (name == "delay_values")
                {
                    return PrimTypes.Map.type;
                }
                else if(name == "fold")
                {
                    if (length(cons.cdr) != 3)
                    {
                        throw new IllegalArgumentException("folds needs 3 arguments, got: " + exp);
                    }
                    Cons c1 = (AST.Cons) cons.cdr;
                    Cons c2 = (AST.Cons) c1.cdr;
                    Cons c3 = (AST.Cons) c2.cdr;

                    ISExp f = infer(c1.car, context, vars, union);
                    ISExp z = infer(c2.car, context, vars, union);
                    ISExp list = infer(c3.car, context, vars, union);
                    // ((A, B) -> B, A, list[A]) -> B
                    ISExp A = vars.getFreshVar(), B = vars.getFreshVar();
                    ISExp fType = absType(ImmutableList.of(A, B), B);
                    unify(f, fType, union);
                    unify(z, B, union);
                    unify(list, PrimTypes.List.type, union);
                    return B;
                }
            }
            ISExp funcType = infer(cons.car, context, vars, union);
            IList args = cons.cdr;
            ImmutableList.Builder<ISExp> argTypesBuilder = ImmutableList.builder();
            while(args != Nil.INSTANCE)
            {
                Cons c2 = (Cons) args;
                argTypesBuilder.add(infer(c2.car, context, vars, union));
                args = c2.cdr;
            }
            ISExp bodyType = vars.getFreshVar();
            unify(funcType, absType(argTypesBuilder.build(), bodyType), union);
            return bodyType;
        }
        return exp.getType();
    }

    static void unify(ISExp first, ISExp second, DisjointSet<ISExp> union)
    {
        ISExp firstLink = find(first, union);
        ISExp secondLink = find(second, union);
        if(firstLink instanceof Symbol)
        {
            union((Symbol) firstLink, second, union);
            return;
        }
        else if(secondLink instanceof Symbol)
        {
            union((Symbol) secondLink, first, union);
            return;
        }
        else if(firstLink instanceof Cons && secondLink == PrimTypes.Map.type)
        {
            Cons type = (Cons) firstLink;
            if(length(type) == 2)
            {
                unify(type.car, PrimTypes.String.type, union);
                return;
            }
        }
        else if(firstLink instanceof IList && secondLink instanceof IList)
        {
            IList firstAbs = (IList) firstLink;
            IList secondAbs = (IList) secondLink;
            while(firstAbs != Nil.INSTANCE && secondAbs != Nil.INSTANCE)
            {
                @SuppressWarnings("ConstantConditions")
                Cons c1 = (Cons) firstAbs;
                Cons c2 = (Cons) secondAbs;
                unify(c1.car, c2.car, union);
                firstAbs = c1.cdr;
                secondAbs = c2.cdr;
            }
            if(firstAbs == secondAbs) // == Nil.INSTANCE
            {
                return;
            }
        }
        else if(firstLink.equals(secondLink))
        {
            return;
        }
        ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap(union);
        throw new IllegalStateException("Type error: can't unify " + typeToString(firstLink, typeMap) + " and " + typeToString(secondLink, typeMap));
    }
}
