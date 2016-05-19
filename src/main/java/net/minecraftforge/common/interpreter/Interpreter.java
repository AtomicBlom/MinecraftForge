package net.minecraftforge.common.interpreter;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import net.minecraftforge.common.animation.ITimeValue;

import static net.minecraftforge.common.interpreter.AST.*;

/**
 * Created by rainwarrior on 5/18/16.
 */
public class Interpreter
{
    private final Function<? super String, ? extends ITimeValue> userParameters;

    public Interpreter(Function<? super String, ? extends ITimeValue> userParameters)
    {
        this.userParameters = userParameters;
    }

    public ISExp eval(ISExp exp)
    {
        return eval(exp, new Cons(new Map(PrimOp.values), Nil.INSTANCE));
    }

    private static final Symbol labelSymbol = new Symbol("&labeled");

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
        return new Cons(new Symbol("&function"), new Cons(args, new Cons(body, new Cons(env, Nil.INSTANCE))));
    }

    private static void addLabel(ImmutableMap.Builder<Symbol, ISExp> frame, ISExp key, ISExp value)
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
            return Unbound.INSTANCE;
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

    @SuppressWarnings("StringEquality")
    private ISExp eval(ISExp exp, IList env)
    {
        if (exp instanceof FloatAtom)
        {
            return exp;
        }
        if (exp instanceof StringAtom)
        {
            return exp;
        }
        else if (exp instanceof Symbol)
        {
            return lookup(env, (Symbol) exp);
        }
        else if (exp instanceof ArithmSymbol)
        {
            return new ArithmOp((ArithmSymbol) exp);
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
                        throw new IllegalArgumentException("quote needs 1 argument, got:" + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return cdr.car;
                }
                else if (name == "lambda")
                {
                    if (length(cons.cdr) != 2)
                    {
                        throw new IllegalArgumentException("lambda needs 2 arguments, got:" + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (!(c2.car instanceof IList))
                    {
                        throw new IllegalArgumentException("lambda needs a list as a first argument, got:" + c2.car);
                    }
                    IList args = (IList) c2.car;
                    ISExp body = c3.car;
                    return makeFunction(args, body, env);
                }
                else if (name == "bind")
                {
                    if (length(cons.cdr) != 2)
                    {
                        throw new IllegalArgumentException("bind needs 2 arguments, got:" + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    ISExp definitions = c2.car;
                    ImmutableMap.Builder<Symbol, ISExp> frame = ImmutableMap.builder();
                    if (definitions instanceof Map)
                    {
                        Map map = (Map) definitions;
                        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                        {
                            addLabel(frame, entry.getKey(), entry.getValue());
                        }
                    }
                    else if (definitions instanceof IList)
                    {
                        IList list = (IList) definitions;
                        while(list != Nil.INSTANCE)
                        {
                            Cons c4 = (Cons) list;
                            if(c4.car instanceof IList && length(c4.car) == 2)
                            {
                                Cons c5 = (Cons) c4.car;
                                Cons c6 = (Cons) c5.cdr;
                                addLabel(frame, c5.car, c6.car);
                            }
                            else
                            {
                                throw new IllegalArgumentException("bind with a list must only have pairs, got: " + c4.car);
                            }
                            list = c4.cdr;
                        }
                    }
                    ISExp body = c3.car;
                    return eval(body, new Cons(new Map(frame.build()), env));
                }
                else if (name == "user")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("user needs 1 argument, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    if (c2.car instanceof StringAtom)
                    {
                        String parameterName = ((StringAtom) c2.car).value;
                        ITimeValue parameter = userParameters.apply(parameterName);
                        if (parameter == null)
                        {
                            return Unbound.INSTANCE;
                        }
                        return new User(parameterName, parameter);
                    }
                    throw new IllegalArgumentException("user needs string argument, got: " + cons.car);
                }
            }
            return apply(eval(cons.car, env), evlis(cons.cdr, env));
        }
        throw new IllegalStateException("eval of " + exp);
    }

    private ISExp apply(ISExp func, IList args)
    {
        if (func instanceof ICallableAtom)
        {
            return ((ICallableAtom) func).apply(args);
        }
        if (func instanceof Cons)
        {
            Cons c1 = (Cons) func;
            if (new Symbol("&function").equals(c1.car) && c1.cdr != Nil.INSTANCE)
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
}
